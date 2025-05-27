package storage

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/credentials"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/s3"
	"github.com/Azure/azure-storage-blob-go/azblob"
	"github.com/spark-hdfs-sas/backend-service/internal/config"
)

type StorageService struct {
	config *config.StorageConfig
	s3Svc  *s3.S3
}

type SASRequest struct {
	Path        string            `json:"path"`
	Operation   string            `json:"operation"` // read, write, delete
	ExpiryHours int               `json:"expiry_hours"`
	Metadata    map[string]string `json:"metadata"`
}

type SASResponse struct {
	URL       string            `json:"url"`
	ExpiresAt time.Time         `json:"expires_at"`
	Metadata  map[string]string `json:"metadata"`
}

func NewStorageService(cfg *config.StorageConfig) *StorageService {
	service := &StorageService{
		config: cfg,
	}

	// Initialize AWS S3 client if configured
	if cfg.AWS.Region != "" {
		sess, err := session.NewSession(&aws.Config{
			Region: aws.String(cfg.AWS.Region),
			Credentials: credentials.NewStaticCredentials(
				cfg.AWS.AccessKeyID,
				cfg.AWS.SecretAccessKey,
				cfg.AWS.SessionToken,
			),
		})
		if err == nil {
			service.s3Svc = s3.New(sess)
		}
	}

	return service
}

func (s *StorageService) GenerateSASURL(ctx context.Context, req *SASRequest) (*SASResponse, error) {
	// Parse the path to determine storage provider
	if strings.HasPrefix(req.Path, "s3://") {
		return s.generateS3SAS(ctx, req)
	} else if strings.HasPrefix(req.Path, "abfs://") || strings.HasPrefix(req.Path, "https://") {
		return s.generateAzureSAS(ctx, req)
	}

	return nil, fmt.Errorf("unsupported storage path: %s", req.Path)
}

func (s *StorageService) generateS3SAS(ctx context.Context, req *SASRequest) (*SASResponse, error) {
	if s.s3Svc == nil {
		return nil, fmt.Errorf("S3 service not configured")
	}

	// Parse S3 path: s3://bucket/key
	parsedURL, err := url.Parse(req.Path)
	if err != nil {
		return nil, fmt.Errorf("invalid S3 path: %w", err)
	}

	bucket := parsedURL.Host
	key := strings.TrimPrefix(parsedURL.Path, "/")

	var presignedURL string
	expiryDuration := time.Duration(req.ExpiryHours) * time.Hour
	expiresAt := time.Now().Add(expiryDuration)

	switch req.Operation {
	case "read":
		getReq, _ := s.s3Svc.GetObjectRequest(&s3.GetObjectInput{
			Bucket: aws.String(bucket),
			Key:    aws.String(key),
		})
		presignedURL, err = getReq.Presign(expiryDuration)
	case "write":
		putReq, _ := s.s3Svc.PutObjectRequest(&s3.PutObjectInput{
			Bucket: aws.String(bucket),
			Key:    aws.String(key),
		})
		presignedURL, err = putReq.Presign(expiryDuration)
	case "delete":
		delReq, _ := s.s3Svc.DeleteObjectRequest(&s3.DeleteObjectInput{
			Bucket: aws.String(bucket),
			Key:    aws.String(key),
		})
		presignedURL, err = delReq.Presign(expiryDuration)
	default:
		return nil, fmt.Errorf("unsupported operation: %s", req.Operation)
	}

	if err != nil {
		return nil, fmt.Errorf("failed to generate S3 presigned URL: %w", err)
	}

	return &SASResponse{
		URL:       presignedURL,
		ExpiresAt: expiresAt,
		Metadata:  req.Metadata,
	}, nil
}

func (s *StorageService) generateAzureSAS(ctx context.Context, req *SASRequest) (*SASResponse, error) {
	if s.config.Azure.AccountName == "" || s.config.Azure.AccountKey == "" {
		return nil, fmt.Errorf("Azure storage not configured")
	}

	// Parse Azure path: abfs://container@account.dfs.core.windows.net/path
	// or https://account.blob.core.windows.net/container/path
	var containerName, blobName, accountName string

	if strings.HasPrefix(req.Path, "abfs://") {
		// Parse ABFS format
		parsedURL, err := url.Parse(req.Path)
		if err != nil {
			return nil, fmt.Errorf("invalid ABFS path: %w", err)
		}
		
		// Extract container and account from host
		hostParts := strings.Split(parsedURL.Host, "@")
		if len(hostParts) != 2 {
			return nil, fmt.Errorf("invalid ABFS host format")
		}
		containerName = hostParts[0]
		accountParts := strings.Split(hostParts[1], ".")
		if len(accountParts) > 0 {
			accountName = accountParts[0]
		}
		blobName = strings.TrimPrefix(parsedURL.Path, "/")
	} else {
		// Parse HTTPS format
		parsedURL, err := url.Parse(req.Path)
		if err != nil {
			return nil, fmt.Errorf("invalid Azure blob path: %w", err)
		}
		
		hostParts := strings.Split(parsedURL.Host, ".")
		if len(hostParts) > 0 {
			accountName = hostParts[0]
		}
		
		pathParts := strings.Split(strings.TrimPrefix(parsedURL.Path, "/"), "/")
		if len(pathParts) > 0 {
			containerName = pathParts[0]
			if len(pathParts) > 1 {
				blobName = strings.Join(pathParts[1:], "/")
			}
		}
	}

	// Create credential
	credential, err := azblob.NewSharedKeyCredential(s.config.Azure.AccountName, s.config.Azure.AccountKey)
	if err != nil {
		return nil, fmt.Errorf("failed to create Azure credential: %w", err)
	}

	// Generate SAS token
	expiryTime := time.Now().Add(time.Duration(req.ExpiryHours) * time.Hour)
	
	var permissions azblob.BlobSASPermissions
	switch req.Operation {
	case "read":
		permissions = azblob.BlobSASPermissions{Read: true}
	case "write":
		permissions = azblob.BlobSASPermissions{Write: true, Create: true}
	case "delete":
		permissions = azblob.BlobSASPermissions{Delete: true}
	default:
		return nil, fmt.Errorf("unsupported operation: %s", req.Operation)
	}

	sasQueryParams, err := azblob.BlobSASSignatureValues{
		Protocol:      azblob.SASProtocolHTTPS,
		ExpiryTime:    expiryTime,
		ContainerName: containerName,
		BlobName:      blobName,
		Permissions:   permissions.String(),
	}.NewSASQueryParameters(credential)

	if err != nil {
		return nil, fmt.Errorf("failed to generate Azure SAS: %w", err)
	}

	// Construct the full URL
	blobURL := fmt.Sprintf("https://%s.blob.core.windows.net/%s/%s?%s",
		s.config.Azure.AccountName, containerName, blobName, sasQueryParams.Encode())

	return &SASResponse{
		URL:       blobURL,
		ExpiresAt: expiryTime,
		Metadata:  req.Metadata,
	}, nil
} 
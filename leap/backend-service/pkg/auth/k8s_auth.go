package auth

import (
	"context"
	"fmt"

	"github.com/spark-hdfs-sas/backend-service/internal/config"
	authv1 "k8s.io/api/authentication/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
)

type K8sAuthService struct {
	client kubernetes.Interface
	config *config.K8sConfig
}

type AuthResult struct {
	Authenticated bool
	Username      string
	Groups        []string
	Namespace     string
	ServiceAccount string
}

func NewK8sAuthService(cfg *config.K8sConfig) *K8sAuthService {
	var kubeConfig *rest.Config
	var err error

	if cfg.InCluster {
		kubeConfig, err = rest.InClusterConfig()
	} else {
		kubeConfig, err = clientcmd.BuildConfigFromFlags("", cfg.ConfigPath)
	}

	if err != nil {
		panic(fmt.Sprintf("Failed to create Kubernetes config: %v", err))
	}

	clientset, err := kubernetes.NewForConfig(kubeConfig)
	if err != nil {
		panic(fmt.Sprintf("Failed to create Kubernetes client: %v", err))
	}

	return &K8sAuthService{
		client: clientset,
		config: cfg,
	}
}

func (k *K8sAuthService) ValidateToken(ctx context.Context, token string) (*AuthResult, error) {
	// Create a TokenReview request
	tokenReview := &authv1.TokenReview{
		Spec: authv1.TokenReviewSpec{
			Token: token,
		},
	}

	// Submit the token review
	result, err := k.client.AuthenticationV1().TokenReviews().Create(ctx, tokenReview, metav1.CreateOptions{})
	if err != nil {
		return nil, fmt.Errorf("failed to validate token: %w", err)
	}

	if !result.Status.Authenticated {
		return &AuthResult{
			Authenticated: false,
		}, nil
	}

	// Extract user information
	user := result.Status.User
	
	return &AuthResult{
		Authenticated:  true,
		Username:       user.Username,
		Groups:         user.Groups,
		Namespace:      extractNamespace(user.Username),
		ServiceAccount: extractServiceAccount(user.Username),
	}, nil
}

// extractNamespace extracts namespace from service account username
// Format: system:serviceaccount:namespace:serviceaccount-name
func extractNamespace(username string) string {
	// Parse the service account username format
	// system:serviceaccount:namespace:serviceaccount-name
	parts := parseServiceAccountUsername(username)
	if len(parts) >= 3 {
		return parts[2]
	}
	return ""
}

// extractServiceAccount extracts service account name from username
func extractServiceAccount(username string) string {
	parts := parseServiceAccountUsername(username)
	if len(parts) >= 4 {
		return parts[3]
	}
	return ""
}

func parseServiceAccountUsername(username string) []string {
	// Simple split by colon - in production you might want more robust parsing
	var parts []string
	current := ""
	for _, char := range username {
		if char == ':' {
			parts = append(parts, current)
			current = ""
		} else {
			current += string(char)
		}
	}
	if current != "" {
		parts = append(parts, current)
	}
	return parts
} 
package services

import (
	"context"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"path/filepath"

	"github.com/open-policy-agent/opa/rego"
	"github.com/spark-hdfs-sas/backend-service/internal/config"
	"github.com/spark-hdfs-sas/backend-service/pkg/auth"
	"github.com/spark-hdfs-sas/backend-service/pkg/storage"
)

type SASService struct {
	storageService *storage.StorageService
	regoConfig     *config.RegoConfig
	policies       map[string]*rego.PreparedEvalQuery
}

type AccessRequest struct {
	User      *auth.AuthResult      `json:"user"`
	Resource  string                `json:"resource"`
	Operation string                `json:"operation"`
	Context   map[string]interface{} `json:"context"`
}

type AccessDecision struct {
	Allowed bool                   `json:"allowed"`
	Reason  string                 `json:"reason"`
	Context map[string]interface{} `json:"context"`
}

func NewSASService(storageService *storage.StorageService, regoConfig *config.RegoConfig) *SASService {
	service := &SASService{
		storageService: storageService,
		regoConfig:     regoConfig,
		policies:       make(map[string]*rego.PreparedEvalQuery),
	}

	// Load Rego policies
	if err := service.loadPolicies(); err != nil {
		// Log error but don't fail - service can work without policies (allow all)
		fmt.Printf("Warning: Failed to load policies: %v\n", err)
	}

	return service
}

func (s *SASService) GenerateSAS(ctx context.Context, authResult *auth.AuthResult, sasReq *storage.SASRequest) (*storage.SASResponse, error) {
	// Check access using Rego policies
	accessReq := &AccessRequest{
		User:      authResult,
		Resource:  sasReq.Path,
		Operation: sasReq.Operation,
		Context: map[string]interface{}{
			"namespace":       authResult.Namespace,
			"service_account": authResult.ServiceAccount,
			"groups":          authResult.Groups,
			"metadata":        sasReq.Metadata,
		},
	}

	decision, err := s.checkAccess(ctx, accessReq)
	if err != nil {
		return nil, fmt.Errorf("access check failed: %w", err)
	}

	if !decision.Allowed {
		return nil, fmt.Errorf("access denied: %s", decision.Reason)
	}

	// Generate SAS URL
	return s.storageService.GenerateSASURL(ctx, sasReq)
}

func (s *SASService) checkAccess(ctx context.Context, req *AccessRequest) (*AccessDecision, error) {
	// If no policies loaded, allow all (for development/testing)
	if len(s.policies) == 0 {
		return &AccessDecision{
			Allowed: true,
			Reason:  "No policies configured - allowing all access",
		}, nil
	}

	// Try each policy until one allows or all deny
	for policyName, query := range s.policies {
		input := map[string]interface{}{
			"user":      req.User,
			"resource":  req.Resource,
			"operation": req.Operation,
			"context":   req.Context,
		}

		results, err := query.Eval(ctx, rego.EvalInput(input))
		if err != nil {
			continue // Try next policy
		}

		if len(results) > 0 && len(results[0].Expressions) > 0 {
			result := results[0].Expressions[0].Value
			if allowed, ok := result.(bool); ok && allowed {
				return &AccessDecision{
					Allowed: true,
					Reason:  fmt.Sprintf("Allowed by policy: %s", policyName),
				}, nil
			}
		}
	}

	return &AccessDecision{
		Allowed: false,
		Reason:  "Access denied by all policies",
	}, nil
}

func (s *SASService) loadPolicies() error {
	if s.regoConfig.PolicyPath == "" {
		return nil
	}

	// Read all .rego files from policy directory
	files, err := filepath.Glob(filepath.Join(s.regoConfig.PolicyPath, "*.rego"))
	if err != nil {
		return fmt.Errorf("failed to find policy files: %w", err)
	}

	for _, file := range files {
		content, err := ioutil.ReadFile(file)
		if err != nil {
			continue
		}

		// Prepare the policy
		query, err := rego.New(
			rego.Query("data.authz.allow"),
			rego.Module(filepath.Base(file), string(content)),
		).PrepareForEval(context.Background())

		if err != nil {
			fmt.Printf("Warning: Failed to prepare policy %s: %v\n", file, err)
			continue
		}

		s.policies[filepath.Base(file)] = &query
	}

	return nil
}

func (s *SASService) ReloadPolicies() error {
	s.policies = make(map[string]*rego.PreparedEvalQuery)
	return s.loadPolicies()
}

func (s *SASService) ListPolicies() []string {
	var names []string
	for name := range s.policies {
		names = append(names, name)
	}
	return names
} 
package handlers

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spark-hdfs-sas/backend-service/internal/services"
	"github.com/spark-hdfs-sas/backend-service/pkg/auth"
	"github.com/spark-hdfs-sas/backend-service/pkg/storage"
)

type SASHandler struct {
	sasService *services.SASService
}

func SetupRoutes(router *gin.RouterGroup, sasService *services.SASService) {
	handler := &SASHandler{
		sasService: sasService,
	}

	router.POST("/sas", handler.GenerateSAS)
	router.GET("/policies", handler.ListPolicies)
	router.POST("/policies/reload", handler.ReloadPolicies)
}

func (h *SASHandler) GenerateSAS(c *gin.Context) {
	// Get auth result from middleware
	authResult, exists := c.Get("auth_result")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Authentication required"})
		return
	}

	auth, ok := authResult.(*auth.AuthResult)
	if !ok {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Invalid authentication data"})
		return
	}

	var req storage.SASRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request format", "details": err.Error()})
		return
	}

	// Validate request
	if req.Path == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Path is required"})
		return
	}

	if req.Operation == "" {
		req.Operation = "read" // Default to read
	}

	if req.ExpiryHours <= 0 {
		req.ExpiryHours = 1 // Default to 1 hour
	}

	// Validate operation
	validOps := map[string]bool{"read": true, "write": true, "delete": true}
	if !validOps[req.Operation] {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid operation. Must be one of: read, write, delete"})
		return
	}

	// Generate SAS URL
	response, err := h.sasService.GenerateSAS(c.Request.Context(), auth, &req)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, response)
}

func (h *SASHandler) ListPolicies(c *gin.Context) {
	policies := h.sasService.ListPolicies()
	c.JSON(http.StatusOK, gin.H{"policies": policies})
}

func (h *SASHandler) ReloadPolicies(c *gin.Context) {
	if err := h.sasService.ReloadPolicies(); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to reload policies", "details": err.Error()})
		return
	}

	policies := h.sasService.ListPolicies()
	c.JSON(http.StatusOK, gin.H{"message": "Policies reloaded successfully", "policies": policies})
} 
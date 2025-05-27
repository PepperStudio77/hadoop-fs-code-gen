package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spark-hdfs-sas/backend-service/internal/config"
	"github.com/spark-hdfs-sas/backend-service/internal/handlers"
	"github.com/spark-hdfs-sas/backend-service/internal/middleware"
	"github.com/spark-hdfs-sas/backend-service/internal/services"
	"github.com/spark-hdfs-sas/backend-service/pkg/auth"
	"github.com/spark-hdfs-sas/backend-service/pkg/storage"
)

func main() {
	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Initialize services
	authService := auth.NewK8sAuthService(cfg.K8s)
	storageService := storage.NewStorageService(cfg.Storage)
	sasService := services.NewSASService(storageService, cfg.Rego)

	// Setup router
	router := gin.Default()
	
	// Add middleware
	router.Use(middleware.CORS())
	router.Use(middleware.Logger())
	router.Use(middleware.K8sAuth(authService))

	// Setup routes
	api := router.Group("/api/v1")
	handlers.SetupRoutes(api, sasService)

	// Health check
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "healthy"})
	})

	// Start server
	srv := &http.Server{
		Addr:    ":" + cfg.Server.Port,
		Handler: router,
	}

	go func() {
		log.Printf("Server starting on port %s", cfg.Server.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Failed to start server: %v", err)
		}
	}()

	// Wait for interrupt signal to gracefully shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Fatal("Server forced to shutdown:", err)
	}

	log.Println("Server exited")
} 
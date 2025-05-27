package config

import (
	"github.com/spf13/viper"
)

type Config struct {
	Server  ServerConfig  `mapstructure:"server"`
	K8s     K8sConfig     `mapstructure:"k8s"`
	Storage StorageConfig `mapstructure:"storage"`
	Rego    RegoConfig    `mapstructure:"rego"`
}

type ServerConfig struct {
	Port string `mapstructure:"port"`
	Host string `mapstructure:"host"`
}

type K8sConfig struct {
	InCluster      bool   `mapstructure:"in_cluster"`
	ConfigPath     string `mapstructure:"config_path"`
	Namespace      string `mapstructure:"namespace"`
	ServiceAccount string `mapstructure:"service_account"`
}

type StorageConfig struct {
	AWS   AWSConfig   `mapstructure:"aws"`
	Azure AzureConfig `mapstructure:"azure"`
}

type AWSConfig struct {
	Region          string `mapstructure:"region"`
	AccessKeyID     string `mapstructure:"access_key_id"`
	SecretAccessKey string `mapstructure:"secret_access_key"`
	SessionToken    string `mapstructure:"session_token"`
}

type AzureConfig struct {
	AccountName   string `mapstructure:"account_name"`
	AccountKey    string `mapstructure:"account_key"`
	ContainerName string `mapstructure:"container_name"`
}

type RegoConfig struct {
	PolicyPath string `mapstructure:"policy_path"`
	DataPath   string `mapstructure:"data_path"`
}

func Load() (*Config, error) {
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")
	viper.AddConfigPath("./configs")
	viper.AddConfigPath(".")

	// Set defaults
	viper.SetDefault("server.port", "8080")
	viper.SetDefault("server.host", "0.0.0.0")
	viper.SetDefault("k8s.in_cluster", true)
	viper.SetDefault("k8s.namespace", "default")
	viper.SetDefault("rego.policy_path", "./policies")
	viper.SetDefault("rego.data_path", "./data")

	// Enable environment variable override
	viper.AutomaticEnv()

	if err := viper.ReadInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return nil, err
		}
	}

	var config Config
	if err := viper.Unmarshal(&config); err != nil {
		return nil, err
	}

	return &config, nil
} 
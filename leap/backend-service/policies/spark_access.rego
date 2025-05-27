package authz

import future.keywords.if
import future.keywords.in

# Default deny
default allow := false

# Allow read access to data in the user's namespace
allow if {
    input.operation == "read"
    input.user.namespace == extract_namespace_from_path(input.resource)
}

# Allow write access to data in the user's namespace for specific service accounts
allow if {
    input.operation == "write"
    input.user.namespace == extract_namespace_from_path(input.resource)
    input.user.service_account in ["spark-driver", "spark-executor"]
}

# Allow delete access only for admin service accounts
allow if {
    input.operation == "delete"
    input.user.service_account in ["spark-admin"]
}

# Allow access to shared data for all authenticated users
allow if {
    startswith(input.resource, "s3://shared-bucket/")
    input.operation == "read"
}

# Allow access to public datasets
allow if {
    startswith(input.resource, "s3://public-datasets/")
    input.operation == "read"
}

# Helper function to extract namespace from resource path
extract_namespace_from_path(path) := namespace if {
    # Extract namespace from path like s3://bucket/namespace/data/file.parquet
    parts := split(trim_prefix(path, "s3://"), "/")
    count(parts) >= 2
    namespace := parts[1]
}

extract_namespace_from_path(path) := namespace if {
    # Extract namespace from Azure path like abfs://container@account.dfs.core.windows.net/namespace/data/file.parquet
    contains(path, "abfs://")
    url_parts := split(path, "/")
    count(url_parts) >= 4
    namespace := url_parts[3]
}

# Default to empty namespace if can't extract
extract_namespace_from_path(path) := "" if {
    not startswith(path, "s3://")
    not contains(path, "abfs://")
} 
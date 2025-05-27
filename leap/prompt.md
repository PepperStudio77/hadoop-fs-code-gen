# One short generation with One Prompt

## Prompts
### Init prompt
```
I want to write a customised HDFS file system for the Spark engine. Such an HDFS file system will talk to a backend service with K8s authentication enabled and retrieve SAS URLs for File systems like S3 or ADFS. It implements all HDFS interfaces and replaces the file directory with SAS just before the Spark engine accesses the file system. The backend service accepts Rego as a rule engine to define ABAC access rules. Have permission to talk to service like S3 to sign HDFS url request from client and return it a SAS URLs for spark client to access it. The client side in spark execurtor, you gonna write it in Java, for the backend services should implemented in Golang . Project structure is been created. 

```

### followup prompt
* Less-Smart: Instead of reusing AWS S3 client standard implementation, it re-implement the SASInputStream and SASOutputStream to support SAS URL.  
```
Underhood the FSFileSystem, for all hadoop fs operation you overwrite like create, append, open, rename. Can you re-use AWS S3 Hadoop FS client instead of implement own SASInputStream and SASOutputStream? 
```







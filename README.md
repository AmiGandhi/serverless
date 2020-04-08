# serverless

Lambda Application for getting bills due with Added Authentication and Access Control for AWS Lambda to CI/CD stack

## Technology Stack

Java 11, Spring Boot 2.2.4, DynamoDB, CloudFormation

## User stories for Web Application REST API call:
API Endpoint: Authenticated HTTP GET to /v1/bills/due/x_days

1. User should be able to request link to all of their recipe’s in the system via email.
2. As a user, I want to get list of bills due in next x days sent to me in an email.
3. HTTP request should return right away and not wait for processing to finish. Processing of the request should be handled in background. When a request is received, post message on SQS queue once the request has been authenticated and authorized.
4. I should only get one email within 60 minute window regardless of how many requests I make. Additional requests made by me in the 60 minute window should be ignored.
5. A separate thread in the application will poll the SQS queue and generate the list. It will then post the list of bills to a SNS topic which will trigger AWS Lambda function. Email will be sent to the user from the Lamdba function.

## Lambda Function specification:

1. Lambda function will be invoked by the SNS notification. 
2. Lambda function is responsible for sending email to the user.
3. As a user, I should be able to only have 1 request token active in database (DynamoDB) at a time.
4. As a user, I expect the request information to be stored in DynamoDB with TTL of 60 minutes.
5. As a user, I expect the request token to expire after 60 minutes if it is not used by then.
6. As a user, I expect the to receive links to all the bills that are due within X days in an email.
7. As a user, if I make multiple requests when there is a active token in the database, I should only receive 1 email.

## CI/CD Pipeline for Lambda Function:

Every commit to the serverless repository should trigger a CircleCI build and deployment of your updates function to AWS Lambda.
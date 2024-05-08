# Springboot-Basic-Login: Spring Webflux + R2dbc
<p> This repository contains a SpringBoot application that works with the basic authentication <b>(email and password)</b> and authorization <b>(user/ admin)</b> </p>

## Setup
### Prerequisites
You will need to pre-installed and basic configure the following software on ypur computer
* [Git](http://git-scm.com/): simply running `git clone https://github.com/laggerbomb/Springboot-Basic-Login.git` to clone this repo.
* [intellij](https://www.jetbrains.com/idea/download/?fromIDE=&section=windows): An IDE for compiling Spring Boot projects; any Java IDE is acceptable, including Eclipse.
* [Docker Desktop](https://docs.docker.com/get-docker/): To effortlessly set up the MailDev server by running the docker-compose.yml file
* [pgAdmin4](https://www.pgadmin.org/download/): To setup and monitor PostgreSQL database
* [Postman](https://www.postman.com/): To send requests, inspect responses, and ensure seamless communication between different components of your application.


### MailDev Installation via Docker
At the of the project directory, there exist two files [Docker compose file](./docker-compose.yml) and [environment variables configuration file](./.env). Kindly configure these 2 files, before running the command below:

```
docker compose up -d
```

### PostgreSQL Database Setup
Create new database which is the same name to the value of "POSTGRES_DB" in the [environment variables configuration file](./.env). In my case the database name is - security_assignment

### Maven build
The `springboot-basic-login` directory contains all the code for our Spring Boot Project. The Maven build should be running automatically, if it not running, open the 'Maven -> Execute Maven Goal' and run the following command
```
mvn clean install
```
Then u can run the project using `mvn spring-boot:run` or click run icon


## Endpoints
### Postman - Register (Admin)
You can create new user by using the following curl on the Postman
 ```sh
curl --location 'http://localhost:8080/register' \
--header 'Content-Type: application/json' \
--data-raw '{
  "username": "Lewis1",
  "password": "test1234",
  "email": "lewis@gmail.com",
  "role": "ADMIN"
}'
```

### Maildev - Verify Email
You can navigate to the `http://localhost:1080` to receive email.

### Swagger - API Documentation
You can access the API documentation and test the functionality by navigating to `http://localhost:8080/swagger-ui.html` before proceeding to test it on Postman.

You might require to enter the ADMIN role username to login into the Swagger. In our case
```
Username : Lewis1
Password : test1234
```


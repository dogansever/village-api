rem mvn clean install

docker build -t dogansever/village-api:latest .

docker run -p8080:8080 dogansever/village-api:latest

FROM maven:3.9.11-eclipse-temurin-17
RUN apt-get update && apt-get install -y --no-install-recommends cmake g++ \
    && rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y --no-install-recommends make \
    && rm -rf /var/lib/apt/lists/*

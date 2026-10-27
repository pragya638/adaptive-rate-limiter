# Adaptive API Rate Limiter (AI-Driven)

Final Year Project: **AI-Driven Adaptive API Rate Limiting Using Traffic Trend Analysis and Anomaly Detection**.

This repository contains a Spring Boot static rate limiter (Phase 1), traffic monitoring and EWMA trend analysis (Phase 3), and a standalone Python FastAPI Isolation Forest anomaly detection service (ml-service, Phase 4). The long-term objective is to combine static throttling, traffic trend analysis, anomaly detection, and an adaptive decision engine to adjust limits dynamically while enforcing safety bounds. The complete adaptive system is still a work in progress; current implementation status is listed below.


## Table of Contents

- [Problem Statement & Motivation](#problem-statement--motivation)
- [Project Objectives](#project-objectives)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [High-Level Architecture](#high-level-architecture)
- [Implementation Status](#implementation-status)
- [Phase 1: Spring Boot Static Rate Limiter](#phase-1-spring-boot-static-rate-limiter)
- [Phase 3: Traffic Monitoring & EWMA](#phase-3-traffic-monitoring--ewma)
- [Phase 4: Python FastAPI Isolation Forest Service (ml-service)](#phase-4-python-fastapi-isolation-forest-service-ml-service)
- [Repository Setup & Quick Start](#repository-setup--quick-start)
- [Running Tests](#running-tests)
- [Service URLs](#service-urls)
- [Example API Calls](#example-api-calls)
- [Limitations](#limitations)
- [Future Work](#future-work)
- [References](#references)

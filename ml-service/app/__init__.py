"""Adaptive Rate Limiter - Phase 4 anomaly detection service package.

This package hosts an independent FastAPI microservice that scores API traffic
features with a scikit-learn Isolation Forest model. It does not import or
depend on the Spring Boot application.
"""

__version__ = "0.1.0"

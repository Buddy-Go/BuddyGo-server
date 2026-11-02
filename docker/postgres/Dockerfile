# 공식 postgis/postgis 이미지는 arm64 빌드가 없어서, 공식 postgres 이미지에 PGDG 패키지로 PostGIS를 얹는다.
FROM postgres:17

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-17-postgis-3 \
    && rm -rf /var/lib/apt/lists/*

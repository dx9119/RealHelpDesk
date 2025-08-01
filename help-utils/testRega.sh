#!/bin/bash

for i in {1..20}; do
  curl -k -X POST https://localhost:8443/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -d '{
      "firstName": "Иван",
      "lastName": "Иванов",
      "email": "test'"$i"'@test.com",
      "password": "test@test.com"
    }'
done

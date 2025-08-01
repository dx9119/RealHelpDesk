#!/bin/bash

portalId="2"

accessToken="eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhcHAiLCJzdWIiOiI1NDNmZDgzZi1iNTBjLTRmNTctODU5MC0wNjYxNmVkOGFiNGMiLCJleHAiOjE3NTQyODM3ODcsImlhdCI6MTc1NDA2Nzc4Nywicm9sZSI6IlJPTEVfVVNFUiIsImF1ZCI6WyJhcHAiXX0.bFA_4sDYtjMwuwWXB0tHqj3ihnrIf2ltmKauJl8DdD4"
refreshToken="eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhcHAiLCJzdWIiOiI1NDNmZDgzZi1iNTBjLTRmNTctODU5MC0wNjYxNmVkOGFiNGMiLCJhdWQiOlsiYXBwIl0sImV4cCI6MTc1OTI1MTc4NywiaWF0IjoxNzU0MDY3Nzg3fQ.QhSkWQ2kCbcp-YqcRZy9owfvxuzkjaCYIFVhDOPkfDw"

for i in {1..20}; do
  curl -k -X POST "https://localhost:8443/api/v1/portals/${portalId}/ticket" \
    -H "Content-Type: application/json" \
    -H "Cookie: accessToken=${accessToken}; refreshToken=${refreshToken}" \
    -d '{
      "title": "Пример заголовка заявки '"$i"'",
      "body": "Описание тестовой заявки №'"$i"' для автоматического создания.",
      "ticketPriority": "LOW"
    }'
  echo -e "\n→ Заявка №${i} отправлена.\n"
done

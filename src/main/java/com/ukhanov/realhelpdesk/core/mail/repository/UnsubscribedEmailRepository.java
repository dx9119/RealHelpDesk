package com.ukhanov.realhelpdesk.core.mail.repository;

import com.ukhanov.realhelpdesk.core.mail.model.UnsubscribedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface UnsubscribedEmailRepository extends JpaRepository<UnsubscribedEmail, String> {

  Optional<UnsubscribedEmail> findByEmail(String email);

  void deleteByEmail(String email);
  
}


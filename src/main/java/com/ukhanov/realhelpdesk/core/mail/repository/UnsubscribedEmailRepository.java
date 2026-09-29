package com.ukhanov.realhelpdesk.core.mail.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.core.mail.model.UnsubscribedEmail;

@Repository
public interface UnsubscribedEmailRepository extends JpaRepository<UnsubscribedEmail, String> {

    Optional<UnsubscribedEmail> findByEmail(String email);

    void deleteByEmail(String email);

}

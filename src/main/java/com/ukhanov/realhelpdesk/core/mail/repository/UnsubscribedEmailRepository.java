package com.ukhanov.realhelpdesk.core.mail.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.core.mail.model.UnsubscribedEmail;

@Repository
public interface UnsubscribedEmailRepository extends JpaRepository<UnsubscribedEmail, Long> {

    Optional<UnsubscribedEmail> findByEmail(String email);

    /** Батч-загрузка стоп-листа по списку адресов: один запрос вместо N проверок при рассылке. */
    List<UnsubscribedEmail> findByEmailIn(Collection<String> emails);

    void deleteByEmail(String email);

}

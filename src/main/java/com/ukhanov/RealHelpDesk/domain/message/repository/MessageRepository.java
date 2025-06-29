package com.ukhanov.RealHelpDesk.domain.message.repository;


import com.ukhanov.RealHelpDesk.domain.message.model.MessageModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageModel, Long> {

    // Найти все обсуждения по ID заявки
    List<MessageModel> findByTicket_Id(Long ticketId);
}

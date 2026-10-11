package com.ukhanov.realhelpdesk.domain.message.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;

@Repository
public interface MessageRepository extends JpaRepository<MessageModel, Long> {

    // Найти все обсуждения по ID заявки.
    // Порядок обязателен: без ORDER BY лента чата приходит в произвольном порядке,
    // который зависит от плана запроса (у вложений и оповещений сортировка уже была).
    @EntityGraph(attributePaths = {"ticket", "author"})
    List<MessageModel> findByTicketIdOrderByIdAsc(Long ticketId);
}

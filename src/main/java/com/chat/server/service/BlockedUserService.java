package com.chat.server.service;

import com.chat.server.dto.request.BlockUserRequestDto;
import com.chat.server.dto.response.BlockedUserDto;
import com.chat.server.entity.BlockedUser;
import com.chat.server.entity.User;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.BlockedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedUserService {

    private final BlockedUserRepository blockedUserRepository;
    private final UserService userService;
    private final ContactService contactService;

    @Transactional
    public BlockedUser blockUser(Long userId, BlockUserRequestDto request) {
        log.info("Blocking user: {} by user: {}", request.getBlockUserUuid(), userId);

        User user = userService.getUserById(userId);
        User blockedUser = userService.getUserByUuid(request.getBlockUserUuid());

        // Нельзя заблокировать себя
        if (userId.equals(blockedUser.getUserId())) {
            throw new ConflictException("Cannot block yourself");
        }

        // Проверяем, не заблокирован ли уже
        if (blockedUserRepository.existsByUserIdAndBlockedUserId(userId, blockedUser.getUserId())) {
            throw new ConflictException("User already blocked");
        }

        // При блокировке удаляем из контактов (если был)
        if (contactService.isContact(userId, blockedUser.getUserId())) {
            contactService.removeContactByUserId(userId, blockedUser.getUserId());
            log.info("Contact removed due to block: {} -> {}", userId, blockedUser.getUserId());
        }

        BlockedUser block = BlockedUser.builder()
                .userId(userId)
                .blockedUserId(blockedUser.getUserId())
                .reason(request.getReason())
                .build();

        BlockedUser savedBlock = blockedUserRepository.save(block);
        log.info("User blocked: {} -> {}", userId, blockedUser.getUserId());

        return savedBlock;
    }

    @Transactional(readOnly = true)
    public List<BlockedUserDto> getBlockedUsers(Long userId) {
        log.debug("Fetching blocked users for user: {}", userId);

        userService.getUserById(userId);

        List<BlockedUser> blockedUsers = blockedUserRepository.findByUserId(userId);

        return blockedUsers.stream()
                .map(block -> {
                    User blockedUserInfo = userService.getUserById(block.getBlockedUserId());
                    return BlockedUserDto.fromEntity(block, blockedUserInfo);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<BlockedUserDto> getBlockedUsersPaginated(Long userId, Pageable pageable) {
        log.debug("Fetching blocked users for user: {} with pagination", userId);

        userService.getUserById(userId);

        Page<BlockedUser> blockedUsers = blockedUserRepository.findByUserId(userId, pageable);

        return blockedUsers.map(block -> {
            User blockedUserInfo = userService.getUserById(block.getBlockedUserId());
            return BlockedUserDto.fromEntity(block, blockedUserInfo);
        });
    }

    @Transactional(readOnly = true)
    public Page<BlockedUserDto> searchBlockedUsers(Long userId, String search, Pageable pageable) {
        log.debug("Searching blocked users for user: {} with query: {}", userId, search);

        userService.getUserById(userId);

        Page<BlockedUser> blockedUsers = blockedUserRepository.searchBlockedUsers(userId, search, pageable);

        return blockedUsers.map(block -> {
            User blockedUserInfo = userService.getUserById(block.getBlockedUserId());
            return BlockedUserDto.fromEntity(block, blockedUserInfo);
        });
    }

    @Transactional(readOnly = true)
    public BlockedUserDto getBlockByUuid(Long userId, UUID blockUuid) {
        log.debug("Getting block by UUID: {} for user: {}", blockUuid, userId);

        BlockedUser block = blockedUserRepository.findByBlockUuid(blockUuid)
                .orElseThrow(() -> new NotFoundException("Block not found"));

        if (!block.getUserId().equals(userId)) {
            throw new NotFoundException("Block not found");
        }

        User blockedUserInfo = userService.getUserById(block.getBlockedUserId());
        return BlockedUserDto.fromEntity(block, blockedUserInfo);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(Long userId, Long blockedUserId) {
        return blockedUserRepository.existsByUserIdAndBlockedUserId(userId, blockedUserId);
    }

    @Transactional(readOnly = true)
    public boolean isMutualBlock(Long userId, Long otherUserId) {
        return blockedUserRepository.isMutualBlock(userId, otherUserId);
    }

    @Transactional
    public void unblockUser(Long userId, UUID blockUuid) {
        log.info("Unblocking user for: {}, block UUID: {}", userId, blockUuid);

        BlockedUser block = blockedUserRepository.findByBlockUuid(blockUuid)
                .orElseThrow(() -> new NotFoundException("Block not found"));

        if (!block.getUserId().equals(userId)) {
            throw new NotFoundException("Block not found");
        }

        blockedUserRepository.delete(block);
        log.info("User unblocked: {} -> {}", userId, block.getBlockedUserId());
    }

    @Transactional
    public void unblockUserByUserId(Long userId, Long blockedUserId) {
        log.info("Unblocking user: {} by user: {}", blockedUserId, userId);

        blockedUserRepository.deleteByUserIdAndBlockedUserId(userId, blockedUserId);
        log.info("User unblocked: {} -> {}", userId, blockedUserId);
    }

    @Transactional
    public void unblockAllUsers(Long userId) {
        log.warn("Unblocking all users for: {}", userId);
        blockedUserRepository.deleteByUserId(userId);
        log.info("All users unblocked for: {}", userId);
    }

    @Transactional(readOnly = true)
    public List<Long> getBlockedUserIds(Long userId) {
        return blockedUserRepository.findBlockedUserIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Long> getUserIdsWhoBlocked(Long blockedUserId) {
        return blockedUserRepository.findUserIdsWhoBlocked(blockedUserId);
    }

    @Transactional(readOnly = true)
    public long getBlockedCount(Long userId) {
        return blockedUserRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean canSendMessage(Long senderId, Long receiverId) {
        // Проверяем, не заблокировал ли отправитель получателя
        if (isBlocked(senderId, receiverId)) {
            log.debug("Sender {} has blocked receiver {}", senderId, receiverId);
            return false;
        }

        // Проверяем, не заблокировал ли получатель отправителя
        if (isBlocked(receiverId, senderId)) {
            log.debug("Receiver {} has blocked sender {}", receiverId, senderId);
            return false;
        }

        return true;
    }
}
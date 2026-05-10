package com.chat.server.service;

import com.chat.server.dto.request.UpdateProfileRequestDto;
import com.chat.server.entity.User;
import com.chat.server.entity.User.Block;
import com.chat.server.entity.User.Contact;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        try {
            Long id = Long.parseLong(userId);
            User user = getUserById(id);

            return new org.springframework.security.core.userdetails.User(
                    user.getUserId().toString(),
                    user.getPasswordHash(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("Invalid user ID format: " + userId);
        }
    }

    @Transactional
    public User createUser(String username, String email, String rawPassword) {
        log.info("Creating user with username: {}", username);

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists: " + username);
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists: " + email);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(User.UserStatus.ACTIVE)
                .isOnline(false)
                .isDeleted(false)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created with id: {}", savedUser.getUserId());

        return savedUser;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#userId")
    public User getUserById(Long userId) {
        log.debug("Fetching user by id: {}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#userUuid")
    public User getUserByUuid(UUID userUuid) {
        log.debug("Fetching user by uuid: {}", userUuid);
        return userRepository.findAllById(userUuid)
                .orElseThrow(() -> new NotFoundException("User not found with uuid: " + userUuid));
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        log.debug("Fetching user by username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        log.debug("Fetching all users");
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<User> searchUsers(String query, Pageable pageable) {
        log.debug("Searching users by query: {}", query);
        return userRepository.searchByUsernameOrEmail(query, pageable);
    }

    @Transactional(readOnly = true)
    public boolean userExists(Long userId) {
        return userRepository.existsById(userId);
    }

    @Transactional(readOnly = true)
    public Long getUserIdByUuid(UUID userUuid) {
        return userRepository.findUserIdByUuid(userUuid)
                .orElseThrow(() -> new NotFoundException("User not found with uuid: " + userUuid));
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public User updateUser(Long userId, UpdateProfileRequestDto request) {
        log.info("Updating user with id: {}", userId);
        User user = getUserById(userId);

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new ConflictException("Username already taken: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email already in use: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
            user.setEmailVerified(false);
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated: {}", updatedUser.getUserId());

        return updatedUser;
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = getUserById(userId);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        log.info("Avatar updated for user: {}", userId);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void deleteAvatar(Long userId) {
        User user = getUserById(userId);
        user.setAvatarUrl(null);
        userRepository.save(user);
        log.info("Avatar deleted for user: {}", userId);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void updateOnlineStatus(Long userId, boolean isOnline) {
        User user = getUserById(userId);
        user.setIsOnline(isOnline);
        user.setLastSeenAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean isUserOnline(UUID userUuid) {
        User user = getUserByUuid(userUuid);
        return Boolean.TRUE.equals(user.getIsOnline());
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(Long userId) {
        log.warn("Deleting user with id: {}", userId);
        User user = getUserById(userId);
        user.setIsDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        user.setStatus(User.UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("User soft deleted: {}", userId);
    }

    @Transactional
    public void addContact(Long userId, UUID contactUuid) {
        User user = getUserById(userId);
        User contact = getUserByUuid(contactUuid);

        if (user.getContacts() == null) {
            user.setContacts(Collections.emptyList());
        }

        boolean alreadyContact = user.getContacts().stream()
                .anyMatch(c -> c.getContactId().equals(contact.getUserId()));

        if (!alreadyContact) {
            Contact newContact = new Contact();
            newContact.setContactId(contact.getUserId());
            newContact.setContactUuid(contact.getUserUuid());
            newContact.setAddedAt(LocalDateTime.now());
            user.getContacts().add(newContact);
            userRepository.save(user);
            log.info("Contact added: {} -> {}", userId, contact.getUserId());
        }
    }

    @Transactional
    public void removeContact(Long userId, UUID contactUuid) {
        User user = getUserById(userId);
        User contact = getUserByUuid(contactUuid);

        if (user.getContacts() != null) {
            user.getContacts().removeIf(c -> c.getContactId().equals(contact.getUserId()));
            userRepository.save(user);
            log.info("Contact removed: {} -> {}", userId, contact.getUserId());
        }
    }

    @Transactional(readOnly = true)
    public List<User> getUserContacts(Long userId) {
        User user = getUserById(userId);
        if (user.getContacts() == null || user.getContacts().isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> contactIds = user.getContacts().stream()
                .map(Contact::getContactId)
                .toList();

        return userRepository.findAllById(contactIds);
    }

    @Transactional
    public void blockUser(Long userId, UUID blockUuid) {
        User user = getUserById(userId);
        User blockUser = getUserByUuid(blockUuid);

        if (user.getBlockedUsers() == null) {
            user.setBlockedUsers(Collections.emptyList());
        }

        boolean alreadyBlocked = user.getBlockedUsers().stream()
                .anyMatch(b -> b.getBlockedId().equals(blockUser.getUserId()));

        if (!alreadyBlocked) {
            Block block = new Block();
            block.setBlockedId(blockUser.getUserId());
            block.setBlockedUuid(blockUser.getUserUuid());
            block.setBlockedAt(LocalDateTime.now());
            user.getBlockedUsers().add(block);
            userRepository.save(user);
            log.info("User blocked: {} -> {}", userId, blockUser.getUserId());
        }
    }

    @Transactional
    public void unblockUser(Long userId, UUID blockUuid) {
        User user = getUserById(userId);
        User blockUser = getUserByUuid(blockUuid);

        if (user.getBlockedUsers() != null) {
            user.getBlockedUsers().removeIf(b -> b.getBlockedId().equals(blockUser.getUserId()));
            userRepository.save(user);
            log.info("User unblocked: {} -> {}", userId, blockUser.getUserId());
        }
    }

    @Transactional(readOnly = true)
    public List<User> getBlockedUsers(Long userId) {
        User user = getUserById(userId);
        if (user.getBlockedUsers() == null || user.getBlockedUsers().isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> blockedIds = user.getBlockedUsers().stream()
                .map(Block::getBlockedId)
                .toList();

        return userRepository.findAllById(blockedIds);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(Long userId, Long targetUserId) {
        User user = getUserById(userId);
        if (user.getBlockedUsers() == null) {
            return false;
        }
        return user.getBlockedUsers().stream()
                .anyMatch(b -> b.getBlockedId().equals(targetUserId));
    }
}
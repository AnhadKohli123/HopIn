package com.hopin.controller;

import com.hopin.model.User;
import com.hopin.service.ChatService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chats;

    public ChatController(ChatService chats) {
        this.chats = chats;
    }

    public record SendReq(String text) {
    }

    @GetMapping
    public List<ChatService.Conversation> list(@RequestAttribute("user") User user) {
        return chats.conversations(user);
    }

    @GetMapping("/{rideId}/{riderId}")
    public ChatService.ChatThread thread(@RequestAttribute("user") User user, @PathVariable Long rideId,
                                         @PathVariable Long riderId) {
        return chats.thread(user, rideId, riderId);
    }

    @PostMapping("/{rideId}/{riderId}")
    public ChatService.MessageView send(@RequestAttribute("user") User user, @PathVariable Long rideId,
                                        @PathVariable Long riderId, @RequestBody SendReq r) {
        return chats.send(user, rideId, riderId, r.text());
    }
}

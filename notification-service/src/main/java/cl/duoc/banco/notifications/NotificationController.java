package cl.duoc.banco.notifications;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Notification> listar(@RequestParam(required = false) String categoria) {
        List<Notification> todas = store.findAll();
        return categoria == null ? todas
                : todas.stream().filter(n -> n.categoria().equalsIgnoreCase(categoria)).toList();
    }
}

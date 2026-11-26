package archivefever.server.notification;

import archivefever.server.user.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's notifications. A request among them is answered where requests
 * are answered: the friend and swap addresses.
 */
@RestController
class NotificationController {

	private final NotificationService notifications;

	private final CurrentUser user;

	NotificationController(NotificationService notifications, CurrentUser user) {
		this.notifications = notifications;
		this.user = user;
	}

	@GetMapping("/api/notifications")
	List<NotificationService.View> list() {
		return notifications.list(user.id());
	}

}

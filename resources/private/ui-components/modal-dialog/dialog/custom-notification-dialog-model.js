LUPAPISTE.CustomNotificationDialogModel = function (params) {
    "use strict";
    var self = this;

    self.id = params.notificationId;

    var notifications = {
        "renewal-upcoming": {
            template: "banner-notification-template",
            image: "/lp-static/img/notifications/upcoming-renewal-banner.png",
            title: "renewal-notification.upcoming.title",
            content: "renewal-notification.upcoming.content",
            readMoreText: "renewal-notification.read-more-text",
            readMoreLink: loc("renewal-notification.read-more-link"),
            closeText: loc("renewal-notification.close")
        },
        "renewal-done": {
            template: "banner-notification-template",
            image: "/lp-static/img/notifications/renewal-done-banner.png",
            title: "renewal-notification.done.title",
            content: "renewal-notification.done.content",
            closeText: loc("renewal-notification.close")
        }
    };

    self.notificationData = notifications[params.notificationId];

};

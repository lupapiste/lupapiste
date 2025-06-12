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
        },
        "lupapiste-gaala": {
            template: "banner-notification-template",
            bottomImage: "/lp-static/img/notifications/lupapiste-gaala-banner.png",
            bottomImageAspectRatio: "0.765",
            content: "lupapiste-gaala-notification.content",
            readMoreText: "lupapiste-gaala-notification.read-more-text",
            readMoreLink: loc("lupapiste-gaala-notification.read-more-link"),
            closeText: loc("lupapiste-gaala-notification.close")
        },
        "lupapiste-gaala-v2": {
            template: "banner-notification-template",
            bottomImage: "/lp-static/img/notifications/lupapiste-gaala-banner.png",
            bottomImageAspectRatio: "0.765",
            content: "lupapiste-gaala-notification-v2.content",
            readMoreText: "lupapiste-gaala-notification.read-more-text",
            readMoreLink: loc("lupapiste-gaala-notification.read-more-link"),
            closeText: loc("lupapiste-gaala-notification.close")
        },
    };

    self.notificationData = notifications[params.notificationId];

};

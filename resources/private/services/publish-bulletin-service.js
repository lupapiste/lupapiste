LUPAPISTE.PublishBulletinService = function() {
  "use strict";
  var self = this;

  hub.subscribe("publishBulletinService::publishBulletin", function(event) {
    ajax.command("publish-bulletin", {id:                   event.appId,
                                      proclamationEndsAt:   event.proclamationEndsAt,
                                      proclamationStartsAt: event.proclamationStartsAt,
                                      proclamationText:     event.proclamationText})
      .success(_.noop)
      .error(_.noop)
      .call();
  });
};

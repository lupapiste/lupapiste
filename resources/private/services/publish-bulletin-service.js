LUPAPISTE.PublishBulletinService = function() {
  "use strict";
  var self = this;

  hub.subscribe("publishBulletinService::publishBulletin", function(event) {
    ajax.command("publish-bulletin", {id: event.id})
      .success(_.noop)
      .error(_.noop)
      .call();
  });
};

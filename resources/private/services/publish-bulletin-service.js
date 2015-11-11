LUPAPISTE.PublishBulletinService = function() {
  "use strict";
  var self = this;

  self.bulletin = ko.observable();

  hub.subscribe("publishBulletinService::publishBulletin", function(event) {
    ajax.command("publish-bulletin", {id:                   event.appId,
                                      proclamationEndsAt:   event.proclamationEndsAt,
                                      proclamationStartsAt: event.proclamationStartsAt,
                                      proclamationText:     event.proclamationText})
      .success(function(res) {
        console.log("succes", res);
      })
      .error(_.noop)
      .call();
  });

  hub.subscribe("publishBulletinService::fetchBulletinVersions", function(event) {
    console.log("id", event);
    ajax.query("bulletin-versions", {bulletinId: event.bulletinId})
      .success(function(res) {
        if (res.bulletin.id) {
          self.bulletin(res.bulletin);
        }
      })
      .call();
    });
};

LUPAPISTE.PublishBulletinService = function() {
  "use strict";
  var self = this;

  self.bulletin = ko.observable();
  self.publishPending = ko.observable(false);

  ko.computed(function() {
    var state = self.publishPending() ? "pending" : "finished";
    hub.send("publishBulletinService::publishProcessing", {state: state});
  });

  var publishBulletin = function(state, event) {
    ajax.command(state + "-bulletin", {id:                  event.id,
                                      proclamationEndsAt:   event.proclamationEndsAt,
                                      proclamationStartsAt: event.proclamationStartsAt,
                                      proclamationText:     event.proclamationText || ""})
      .pending(self.publishPending)
      .success(function() {
        hub.send("publishBulletinService::publishProcessed", {status: "success"});
      })
      .error(function() {
        hub.send("publishBulletinService::publishProcessed", {status: "failed"});
      })
      .call();
  };

  hub.subscribe("publishBulletinService::proclaimBulletin", function(event) {
    publishBulletin("proclaim", event);
  });

  var fetchBulletinVersions = _.debounce(function(bulletinId) {
    ajax.query("bulletin-versions", {bulletinId: bulletinId})
      .success(function(res) {
        if (util.getIn(res, ["bulletin", "id"])) {
          self.bulletin(res.bulletin);
        }
      })
      .call();
  });

  hub.subscribe("publishBulletinService::fetchBulletinVersions", function(event) {
    fetchBulletinVersions(event.bulletinId);
  });

};

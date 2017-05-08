LUPAPISTE.PublishBulletinService = function() {
  "use strict";
  var self = this;

  self.bulletin = ko.observable();

  self.publishPending = ko.observable(false);

  self.comments = ko.observable([]);

  self.commentsLeft = ko.observable(0);

  self.totalComments = ko.observable();

  ko.computed(function() {
    var state = self.publishPending() ? "pending" : "finished";
    hub.send("publishBulletinService::publishProcessing", {state: state});
  });

  var publishBulletin = function(command, opts) {
    ajax.command(command, opts)
      .pending(self.publishPending)
      .success(function() {
        hub.send("publishBulletinService::publishProcessed", {status: "success"});
      })
      .error(function(resp) {
        hub.send("publishBulletinService::publishProcessed", {status: "failed", text: resp.text});
      })
      .call();
  };

  hub.subscribe("publishBulletinService::moveToProclaimed", function(event) {
    publishBulletin("move-to-proclaimed", {id: event.id,
                                           proclamationEndsAt:   event.proclamationEndsAt,
                                           proclamationStartsAt: event.proclamationStartsAt,
                                           proclamationText:     event.proclamationText});
  });

  hub.subscribe("publishBulletinService::saveProclaimedBulletin", function(event) {
    publishBulletin("save-proclaimed-bulletin", {bulletinId:           event.bulletinId,
                                                 bulletinVersionId:    event.bulletinVersionId,
                                                 proclamationEndsAt:   event.proclamationEndsAt,
                                                 proclamationStartsAt: event.proclamationStartsAt,
                                                 proclamationText:     event.proclamationText});
  });

  hub.subscribe("publishBulletinService::moveToVerdictGiven", function(event) {
    publishBulletin("move-to-verdict-given", {id: event.id,
                                              verdictGivenAt:       event.verdictGivenAt,
                                              appealPeriodStartsAt: event.appealPeriodStartsAt,
                                              appealPeriodEndsAt:   event.appealPeriodEndsAt,
                                              verdictGivenText:     event.verdictGivenText});
  });

  hub.subscribe("publishBulletinService::saveVerdictGivenBulletin", function(event) {
    publishBulletin("save-verdict-given-bulletin", {bulletinId:           event.bulletinId,
                                                    bulletinVersionId:    event.bulletinVersionId,
                                                    verdictGivenAt:       event.verdictGivenAt,
                                                    appealPeriodStartsAt: event.appealPeriodStartsAt,
                                                    appealPeriodEndsAt:   event.appealPeriodEndsAt,
                                                    verdictGivenText:     event.verdictGivenText});
  });

  hub.subscribe("publishBulletinService::moveToFinal", function(event) {
    publishBulletin("move-to-final", {id: event.id,
                                      officialAt: event.officialAt });
  });

  var fetchBulletinVersions = _.debounce(function(bulletinId) {
    ajax.query("bulletin-versions", {bulletinId: bulletinId})
      .success(function(res) {
        if (util.getIn(res, ["bulletin", "id"])) {
          self.bulletin(res.bulletin);
        } else {
          self.bulletin(undefined);
        }
      })
      .call();
  }, 100);

  hub.subscribe("publishBulletinService::fetchBulletinVersions", function(event) {
    fetchBulletinVersions(event.bulletinId);
  });

  // bulletin comment pagination
  var skip = 0;
  var limit = 5;
  var previousVersionId;
  var previousAsc = false;
  var ajaxRunning = false;

  var fetchBulletinComments = _.debounce(function(bulletinId, versionId, asc) {
    if (ajaxRunning) {
      return;
    }
    ajaxRunning = true;
    ajax.query("bulletin-comments", {bulletinId: bulletinId,
                                     versionId: versionId,
                                     skip: skip,
                                     limit: limit,
                                     asc: asc})
      .success(function(res) {
        self.comments(self.comments().concat(res.comments));
        self.commentsLeft(res.commentsLeft);
        self.totalComments(res.totalComments);
        skip += limit;
        previousAsc = asc;
      })
      .complete(function() {
        ajaxRunning = false;
      })
      .call();
  }, 400);

  hub.subscribe("publishBulletinService::fetchBulletinComments", function(event) {
    if (event.initialQuery || event.versionId !== previousVersionId || event.asc !== previousAsc) {
      skip = 0;
      previousVersionId = event.versionId;
      self.comments([]);
    }
    fetchBulletinComments(event.bulletinId, event.versionId, event.asc);
  });
};

LUPAPISTE.BulletinCommentsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.showVersionComments = params.showVersionComments;

  self.bulletin = params.bulletin

  self.comments = params.comments;

  self.commentsLeft = ko.pureComputed(function() {
    return params.commentsLeft() > 0;
  }).extend({notify: "always"});

  self.fetchComments = _.debounce(function() {
    var bulletinId = util.getIn(self, ["bulletin", "id"]);
    var versionId = util.getIn(self, ["showVersionComments", "id"]);
    self.sendEvent("publishBulletinService", "fetchBulletinComments", {bulletinId: bulletinId,
                                                                       versionId: versionId});
  }, 50);

  self.hideComments = function() {
    self.showVersionComments(undefined);
  };

  self.proclaimedHeader = ko.pureComputed(function() {
    var start  = util.getIn(self, ["showVersionComments", "proclamationStartsAt"], "");
    var end    = util.getIn(self, ["showVersionComments", "proclamationEndsAt"], "");
    var amount = util.getIn(self.comments().length);
    if (start && end) {
      return loc("bulletin.proclaimedHeader.duringProclamation") + " " + moment(start).format("D.M.YYYY") + " - " + moment(end).format("D.M.YYYY") +
        " " + loc("bulletin.proclaimedHeader.givenComments") + " " + amount + " kpl."
    }
  });
};

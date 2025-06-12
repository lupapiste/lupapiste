LUPAPISTE.YmpBulletinCommentsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.bulletinVersion = params.bulletinVersion;

  self.bulletin = params.bulletin;

  self.comments = params.comments;

  self.application = params.application;

  var repliesService = lupapisteApp.services.repliesService;

  self.commentsLeft = self.disposedPureComputed(function() {
    return params.commentsLeft() > 0;
  });

  self.totalComments = params.totalComments;

  self.asc = ko.observable(false);

  var initialQuery = true;

  self.fetchComments = function() { // this is called from infinite-scroll component
    var bulletinId = util.getIn(self, ["bulletin", "id"]);
    var versionId = util.getIn(self, ["bulletinVersion", "id"]);
    self.sendEvent("publishBulletinService", "fetchBulletinComments", {bulletinId: bulletinId,
                                                                       versionId: versionId,
                                                                       asc: self.asc(),
                                                                       initialQuery: initialQuery});
    initialQuery = false;
  };

  self.description = function(comment) {
    var contactInfo = comment["contact-info"];
    var name = _.filter([contactInfo.lastName, contactInfo.firstName]).join(" ");
    var city = _.filter([contactInfo.zip, contactInfo.city]).join(" ");
    var email = contactInfo.email;
    var emailPreferred = contactInfo.emailPreferred ? loc("bulletin.emailPreferred") : undefined;
    return _.filter([name, contactInfo.street, city, email, emailPreferred]).join(", ");
  };

  self.disposedSubscribe( self.asc, function() {
    if (!initialQuery) {
      initialQuery = true;
      self.fetchComments();
    }
  });

  self.selectedComment = ko.observable();

  self.selectComment = function(comment) {
    if (comment.id) {
      if (self.selectedComment() === comment.id) {
        self.selectedComment(undefined);
      } else {
        self.selectedComment(comment.id);
      }
    }
  };

  self.hideComments = function() {
    self.bulletinVersion(undefined);
  };

  self.proclaimedHeader = self.disposedPureComputed(function() {
    var start  = util.getIn(self, ["bulletinVersion", "proclamationStartsAt"], "");
    var end    = util.getIn(self, ["bulletinVersion", "proclamationEndsAt"], "");
    if (start && end) {
      return loc("bulletin.proclaimedHeader.duringProclamation") + " " + moment(start).format("D.M.YYYY") + " - " + moment(end).format("D.M.YYYY") +
        " " + loc("bulletin.proclaimedHeader.givenComments") + " " + self.totalComments() + " kpl.";
    }
  });

  self.sortButtonText = self.disposedPureComputed(function() {
    if (self.asc()) {
      return "bulletin.comments.sort.asc";
    } else {
      return "bulletin.comments.sort.desc";
    }
  });

  self.commentIndex = function(index) {
    return self.asc() ? index + 1 : self.totalComments() - index;
  };

  self.getReplyStatus = function(bulletin) {
    return (bulletin.id && bulletin.bulletinId)
      ? repliesService.getReplyStatus(bulletin.id, bulletin.bulletinId)
      : null;
  };

  self.createReply = function(bulletin) {
    repliesService.createNewReply(bulletin.id, "bulletin", bulletin.bulletinId);
  };

  self.canReply = function( bulletin ) {
    var authed = lupapisteApp.models.applicationAuthModel.ok( "create-reply" );
    return self.getReplyStatus( bulletin ) === "no-reply" && authed;
  };

  self.goToReplies = function(bulletin) {
    repliesService.goToReplies(bulletin.bulletinId);
  };

  repliesService.clearStatuses();

  self.fetchComments(); // initial fetch
};

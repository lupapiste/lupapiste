LUPAPISTE.BulletinCommentModel = function() {
  "use strict";
  var self = this;

  self.comment = ko.observable();

  self.addAttachment = function() {
    // TODO add attachment to comment before it is send
  };

  self.sendComment = function(form) {
    hub.send("bulletinService::sendComment", {commentForm: form});
  };
};

LUPAPISTE.BulletinCommentModel = function() {
  "use strict";
  var self = this;

  self.comment = ko.observable();

  self.addComment = function() {
    hub.send("bulletinService::commentAdded", {comment: "I am a teapot"});
  };

};

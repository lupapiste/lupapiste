LUPAPISTE.BulletinCommentModel = function() {
  "use strict";
  var self = this;

  self.addComment = function() {
    hub.send("bulletinService::commentAdded", {comment: "I am a teapot"});
  };

};

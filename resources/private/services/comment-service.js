LUPAPISTE.CommentService = function() {
  "use strict";
  var self = this;

  self.comments = ko.observableArray([]);

  self.queryComments = function() {
    var authorization = lupapisteApp.models.applicationAuthModel;
    var id = lupapisteApp.models.application.id();
    if (id && authorization.ok("comments")) {
      ajax.query("comments", {id: id})
        .success(function (res) {
          self.comments(res.comments);
        })
        .error(function(e) {
          self.comments([]);
          error("Unable to load comments for " + id + ": " + e.text);
        })
        .call();
    }
  };

  hub.subscribe("application-model-updated", self.queryComments);
  hub.subscribe("attachmentsService::remove", self.queryComments);
  hub.subscribe("attachmentsService::query", function( event ) {
    if( _.includes( ["attachment", "attachments"], event.key )) {
      self.queryComments();
    }
  });
};

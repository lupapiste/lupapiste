var comments = (function() {
  "use strict";

  function CommentModel() {
    var self = this;

    self.target = {type: "application"};
    self.text = ko.observable();
    self.comments = ko.observableArray();

    self.setComments = function(comments) {
      var filteredComments =
        _.filter(comments,
            function(comment) {
              return self.target.type === comment.target.type && self.target.id === comment.target.id;
            });
      self.comments(ko.mapping.fromJS(filteredComments));
    };

    self.setTarget = function(target) {
      self.target = target;
    };

    self.setApplicationId = function(applicationId) {
      self.applicationId = applicationId;
    };

    self.disabled = ko.computed(function() {
      return _.isEmpty(self.text());
    });

    self.submit = function(model) {
      var id = self.applicationId;
      ajax.command("add-comment", { id: id, text: model.text(), target: self.target})
        .success(function() {
          model.text("");
          repository.load(id);
        })
        .call();
      return false;
    };

    self.category = function(model) {
      return model.target && model.target.version ? "category-new-attachment-version" : "category-default";
    };
}

  return {
    create: function() { return new CommentModel(); }
  };

})();

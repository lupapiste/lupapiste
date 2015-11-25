LUPAPISTE.BulletinCommentsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.showVersionComments = params.showVersionComments;

  self.bulletin = params.bulletin
  self.comments = ko.computed(function() {
    return util.getIn(self, ["bulletin", "comments", util.getIn(self, ["showVersionComments", "id"])]);
  });

  self.hideComments = function() {
    self.showVersionComments(undefined);
  };
};

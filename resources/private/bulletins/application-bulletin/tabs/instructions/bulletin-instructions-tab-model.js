LUPAPISTE.BulletinInstructionsTabModel = function(params) {
  "use strict";
  var self = this;

  self.bulletin = params.bulletin;

  self.inProclaimedState = ko.pureComputed(function() {
    return _.includes(["proclaimed"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.inVerdictGivenState = ko.pureComputed(function() {
    return _.includes(["verdictGiven"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.inFinalState = ko.pureComputed(function() {
    return "final" === util.getIn(self, ["bulletin", "bulletinState"]);
  });

  self.proclaimedText = ko.pureComputed(function() {
    return util.getIn(params, ["bulletin", "proclamationText"]);
  });

  self.verdictGivenText = ko.pureComputed(function() {
    return util.getIn(params, ["bulletin", "verdictGivenText"]);
  });

};

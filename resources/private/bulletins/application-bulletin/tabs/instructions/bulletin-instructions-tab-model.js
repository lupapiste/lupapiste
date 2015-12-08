LUPAPISTE.BulletinInstructionsTabModel = function(params) {
  "use strict";
  var self = this;

  self.proclaimedText = ko.pureComputed(function() {
    return util.getIn(params, ["bulletin", "proclamationText"]);
  });

  self.verdictGivenText = ko.pureComputed(function() {
    return util.getIn(params, ["bulletin", "verdictGivenText"]);
  });

};

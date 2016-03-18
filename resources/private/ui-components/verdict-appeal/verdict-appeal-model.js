LUPAPISTE.VerdictAppealModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.appeals = ko.observableArray();

  self.newBubbleVisible = ko.observable();

  self.toggleNewBubble = function() {
    self.newBubbleVisible( !self.newBubbleVisible());
  };

  self.waiting = ko.observable();
};

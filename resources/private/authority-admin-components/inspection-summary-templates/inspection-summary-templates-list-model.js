LUPAPISTE.InspectionSummaryTemplatesListModel = function() {
  "use strict"

  var self = this;

  self.newTemplateBubbleVisible = ko.observable(false);

  self.toggleNewTemplateBubble = function() {
    var old = self.newTemplateBubbleVisible();
    self.newTemplateBubbleVisible(!old);
  };
};
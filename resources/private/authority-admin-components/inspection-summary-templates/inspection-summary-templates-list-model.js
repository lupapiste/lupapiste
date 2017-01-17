LUPAPISTE.InspectionSummaryTemplatesListModel = function() {
  "use strict"

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  lupapisteApp.services.inspectionSummaryService.getTemplatesAsAuthorityAdmin();

  self.templates = ko.observableArray([]);

  self.newTemplateBubbleVisible = ko.observable(false);

  self.toggleNewTemplateBubble = function() {
    var old = self.newTemplateBubbleVisible();
    self.newTemplateBubbleVisible(!old);
  };

  self.addEventListener("inspectionSummaryService", "templatesLoaded", function(event) {
    self.templates(_.map(event.templates, function(n) {
      return _.set(n, 'editorVisible', ko.observable(false));
    }));
  });

  self.toggleOpenDetails = function() {
    var old = this.editorVisible();
    this.editorVisible(!old);
  };
};
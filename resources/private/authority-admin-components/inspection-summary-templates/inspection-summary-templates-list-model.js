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
    var templates = _.clone(event.templates);
    self.templates(_.map(templates, function(n) {
      n.editorVisible = ko.observable(false);
      n.templateText = _.join(n.items, "\n");
      return n;
    }));
  });

  self.toggleOpenDetails = function() {
    var old = this.editorVisible();
    this.editorVisible(!old);
  };

  self.remove = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"),
                                           loc("auth-admin.inspection-summary-template.confirmdelete"),
                                           {title: loc("yes"), fn: _.bind(self.removeTemplate, this)});
  };

  self.removeTemplate = function() {
    lupapisteApp.services.inspectionSummaryService.deleteTemplateById(this.id);
  };
};
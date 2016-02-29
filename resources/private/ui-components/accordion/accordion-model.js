LUPAPISTE.AccordionModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.ltitle = params.ltitle;
  self.accordionContentTemplate = params.accordionContentTemplate;
  self.accordionContentTemplateData = params.accordionContentTemplateData;
};

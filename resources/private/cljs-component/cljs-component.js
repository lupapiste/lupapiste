LUPAPISTE.CljsComponentModel = function(params) {
  "use strict";
  var self = this;

  self.componentName = params.name;

  $.getScript('/lp-static/js/'+self.componentName+'.js', function() {
      lupapalvelu.ui[_.snakeCase(self.componentName)].start(self.componentName);
  });
};


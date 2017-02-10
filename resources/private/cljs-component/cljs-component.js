LUPAPISTE.CljsComponentModel = function(params) {
  "use strict";
  var self = this;
  self.componentName = params.componentName;
/*
  if (component) { // If React component is already loaded, defer mounting it so #id for template is bound after KO compnent init
    //_.defer(component.start, self.componentName, params.app);
    // _.defer(component.start, "cljs-component-template", params.app);
  } else {
      //lupapalvelu.ui[_.snakeCase(self.componentName)].start(self.componentName, params.app);
  }
*/
  self.dispose = function() {
    ReactDOM.unmountComponentAtNode(document.getElementById(params.elemId));
  };

  _.defer(lupapalvelu.ui[self.componentName].start, params.elemId, params);
};


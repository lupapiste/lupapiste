LUPAPISTE.CljsComponentModel = function(params) {
  "use strict";
  var self = this;

  self.componentName = params.name;
  var component = lupapalvelu.ui[_.snakeCase(self.componentName)];

  if (component) { // If React component is already loaded, defer mounting it so #id for template is bound after KO compnent init
    _.defer(component.start, self.componentName);
  } else {
    $.getScript('/lp-static/js/'+self.componentName+'.js', function() {
      lupapalvelu.ui[_.snakeCase(self.componentName)].start(self.componentName);
    });
  }

  self.dispose = function(param) {
    ReactDOM.unmountComponentAtNode(document.getElementById(self.componentName));
  }
};
/*
ko.components.register("cljs-component", {
  viewModel: {createViewModel: function(params, componentInfo) {
            // - 'params' is an object whose key/value pairs are the parameters
            //   passed from the component binding or custom element
            // - 'componentInfo.element' is the element the component is being
            //   injected into. When createViewModel is called, the template has
            //   already been injected into this element, but isn't yet bound.
            // - 'componentInfo.templateNodes' is an array containing any DOM
            //   nodes that have been supplied to the component. See below.
            // Return the desired view model instance, e.g.:
            return new LUPAPISTE.CljsComponentModel(params, componentInfo.element);
  }},
  template: {element: "cljs-component-template"}
});*/



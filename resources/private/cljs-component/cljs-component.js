/**
 * Used for custom elements starting with <cljs*...
 * Inits the CLJS component by calling start-method with the elementId, where React component content will be injected.
 * Params given for cljs-* Knockout components will be passed as second parameter for CLJS component.
 * Thus CLJS component must implement start function, which is given two parameters: DOM element id and parameters passed to Knockout component.
 */
LUPAPISTE.CljsComponentModel = function(params) {
  "use strict";
  var self = this;
  self.componentPath = params.componentPath;

  self.dispose = function() {
    ReactDOM.unmountComponentAtNode(document.getElementById(params.elemId));
  };

  // bind React component only after template has been injected
  _.defer((_.get(lupapalvelu.ui, self.componentPath) || _.get(lupapalvelu, self.componentPath)).start, params.elemId, params);
};

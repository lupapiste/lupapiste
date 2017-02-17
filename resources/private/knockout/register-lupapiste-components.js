ko.registerLupapisteComponents = function(components) {
  "use strict";

  _.forEach(components, function(component) {
    var opts = {viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name + "Model"))],
                template: { element: (component.template ? component.template : component.name + "-template")},
                synchronous: Boolean(component.synchronous)};
    ko.components.register(component.name, opts);
  });
};

var origGetComponentNameForNode = ko.components.getComponentNameForNode;
ko.components.getComponentNameForNode = function(node) {
  "use strict";
  var nodeLower = node.tagName && node.tagName.toLowerCase();
  if (_.startsWith(nodeLower, "cljs-")) {
    return nodeLower;
  } else {
    return origGetComponentNameForNode(node);
  }
};

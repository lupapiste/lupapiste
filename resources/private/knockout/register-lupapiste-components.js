ko.registerLupapisteComponents = function(components) {
  "use strict";

  _.forEach(components, function(component) {
    var opts = {viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name + "Model"))],
                template: { element: (component.template ? component.template : component.name + "-template")},
                synchronous: Boolean(component.synchronous)};
    ko.components.register(component.name, opts);
  });
};

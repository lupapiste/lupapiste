LUPAPISTE.DocgenButtonModel = function(params) {
  "use strict";

  _.extend(this, params, {
    id: params.id,
    clickFn: params.clickFn || _.noop,
    label: params.label,
    icon: params.icon,
    className: params.className || "primary",
    testId: params.testId || ""
  });

};

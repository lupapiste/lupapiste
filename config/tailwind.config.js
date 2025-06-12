/** @type {import('tailwindcss').Config} */
const config = {
  content: [
    "./src-cljs/**/*.cljs",
    "./resources/private/**/*.html",
    "./src/lupapalvelu/document/schemas.clj"
  ],
}

exports.default = config;
